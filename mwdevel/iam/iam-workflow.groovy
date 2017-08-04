#!/usr/bin/env groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  parameters([
    string(name: 'REPO',             defaultValue: 'https://github.com/marcocaberletti/iam.git',                 description: 'IAM code repository'),
    string(name: 'BRANCH',           defaultValue: 'develop',                                                    description: 'IAM code branch'),
    string(name: 'TESTSUITE_REPO',   defaultValue: 'https://github.com/marcocaberletti/iam-robot-testsuite.git', description: 'Testsuite code repository'),
    string(name: 'TESTSUITE_BRANCH', defaultValue: 'develop',                                                    description: 'Testsuite code repository'),
    choice(name: 'CONTEXT',          choices:      'dev\nprod',                                                  description: 'Infrastructure'),
  ]),
])


def registry
def approver
def login_service_image_name = "indigoiam/iam-login-service"
def test_client_image_name = "indigoiam/iam-test-client"
def login_service_version
def test_client_image_tag = 'latest'
def iam_build_job

stage('build & analyze'){
  iam_build_job = build job: 'iam-build',
  parameters: [
    string(name: 'REPO',   value: "${params.REPO}"),
    string(name: 'BRANCH', value: "${params.BRANCH}"),
  ]
}


stage("Promote to QA") {
  if(!'SUCCESS'.equals(iam_build_job.result)) {
    timeout(time: 60, unit: 'MINUTES'){
      approver = input(message: 'Promote to QA?', submitterParameter: 'approver')
    }
  }else {
    echo "Autopromote to QA"
  }
}


stage("Docker images") {

  node('generic'){
    step ([$class: 'CopyArtifact',
      projectName: 'iam-build',
      filter: 'version.txt',
      selector: [$class: 'SpecificBuildSelector', buildNumber: "${iam_build_job.number}"]
    ])
    login_service_version = readFile 'version.txt'
    login_service_version = login_service_version.trim()
    registry = "${env.DOCKER_REGISTRY_HOST}"
  }

  build job: 'docker_build-iam-image',
  parameters: [
    string(name:'IAM_REPO', value: "${params.REPO}"),
    string(name:'IAM_BRANCH', value: "${params.BRANCH}"),
    string(name:'LOGIN_SERVICE_IMAGE_NAME', value: "${login_service_image_name}"),
    string(name:'LOGIN_SERVICE_VERSION', value: "${login_service_version}"),
    string(name:'TEST_CLIENT_IMAGE_NAME', value: "${test_client_image_name}"),
    string(name:'ARTIFACT_FROM_BUILD', value:"${iam_build_job.result}")
  ]
}

def iam_image = "${image_name}:${login_service_version}-latest"
def test_client_image = "${test_client_image_name}:${test_client_image_tag}"

stage("Selenium testsuite"){
  def test_chrome
  def test_ff

  parallel(
      "develop-chrome": {
        test_chrome = build job: 'iam-deployment-test', propagate: false,
        parameters: [
          string(name: 'BROWSER',          value: 'chrome'),
          string(name: 'IAM_IMAGE',        value: "${iam_image}"),
          string(name: 'TESTSUITE_REPO',   value: "${params.TESTSUITE_REPO}"),
          string(name: 'TESTSUITE_BRANCH', value: "${params.TESTSUITE_BRANCH}"),
        ]
      },
      "develop-firefox": {
        test_ff = build job: 'iam-deployment-test', propagate: false,
        parameters: [
          string(name: 'BROWSER',          value: 'firefox'),
          string(name: 'IAM_IMAGE',        value: "${iam_image}"),
          string(name: 'TESTSUITE_REPO',   value: "${params.TESTSUITE_REPO}"),
          string(name: 'TESTSUITE_BRANCH', value: "${params.TESTSUITE_BRANCH}"),
        ]
      },
      )

  if("FAILURE".equals(test_chrome.result) && "FAILURE".equals(test_ff.result)) {
    currentBuild.result = 'FAILURE'
  }
}


stage("Promote to staging?") {

  timeout(time: 60, unit: 'MINUTES'){
    approver = input(message: 'Promote to STAGING?', submitterParameter: 'approver')
  }
}


stage("Deploy to staging") {

  build job: 'iam-login-service-kube-deploy', parameters: [
    string(name: 'ENVIRONMENT', value: 'STAGING'),
    string(name: 'IAM_IMAGE',   value: "${registry}/${iam_image}"),
    string(name: 'CONTEXT',     value: "prod"),
  ]

  build job: 'iam-test-client-kube-deploy', parameters: [
    string(name: 'ENVIRONMENT',           value: 'STAGING'),
    string(name: 'IAM_TEST_CLIENT_IMAGE', value: "${registry}/${test_client_image}"),
    string(name: 'CONTEXT',               value: "prod"),
  ]
}


stage("Test deploy"){
  node('kubectl'){
    sh "git clone https://github.com/marcocaberletti/iam-deployment-test.git"
    sh "git clone git@baltig.infn.it:caberletti/kube_deployments.git"

    sh "kubectl apply -f kube_deployments/iam/staging/ts-params.cm.yaml --namespace staging"

    def pod_name = "iam-ts-${UUID.randomUUID().toString()}"
    def report_dir = "/srv/scratch/${pod_name}/reports"

    dir('iam-deployment-test/kubernetes'){
      withEnv([
        "IAM_BASE_URL=https://cloud-vm195.cloud.cnaf.infn.it",
        "NAMESPACE=staging",
        "TESTSUITE_OPTS=--include=test-client --include=token",
        "POD_NAME=${pod_name}",
        "OUTPUT_REPORTS=${report_dir}",
      ]){
        try {
          sh "./generate_ts_pod_conf.sh"
          sh "kubectl apply -f iam-testsuite.pod.yaml --namespace ${NAMESPACE}"
          sh "while ( [ 'Running' != `kubectl get pod $POD_NAME --namespace ${NAMESPACE} -o jsonpath='{.status.phase}'` ] ); do echo 'Waiting testsuite...'; sleep 5; done"

          sh "kubectl logs -f $POD_NAME --namespace ${NAMESPACE}"

          sh "kubectl delete -f iam-testsuite.pod.yaml --namespace ${NAMESPACE}"

          dir("${report_dir}") {
            step([$class: 'RobotPublisher',
              disableArchiveOutput: false,
              logFileName: 'log.html',
              otherFiles: '*.png',
              outputFileName: 'output.xml',
              outputPath: ".",
              passThreshold: 100,
              reportFileName: 'report.html',
              unstableThreshold: 90])
          }
          currentBuild.result = 'SUCCESS'
        }catch(error) {
          currentBuild.result = 'FAILURE'
        }
      }
    }
  }
}


stage("Promote to production?") {

  timeout(time: 60, unit: 'MINUTES'){
    approver = input(message: 'Promote to PRODUCTION?', submitterParameter: 'approver')
  }
}


stage("Production") {

  build job: 'iam-kube-deploy', parameters: [
    string(name: 'ENVIRONMENT', value: 'PRODUCTION'),
    string(name: 'IAM_IMAGE',   value: "${registry}/${iam_image}"),
    string(name: 'CONTEXT',     value: "${params.CONTEXT}"),
  ]

  build job: 'iam-test-client-kube-deploy', parameters: [
    string(name: 'ENVIRONMENT',           value: 'PRODUCTION'),
    string(name: 'IAM_TEST_CLIENT_IMAGE', value: "${registry}/${test_client_image}"),
    string(name: 'CONTEXT',               value: "${params.CONTEXT}"),
  ]
}
