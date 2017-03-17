properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    string(name: 'REPO',             defaultValue: 'https://github.com/marcocaberletti/iam.git',                 description: 'IAM code repository'),
    string(name: 'BRANCH',           defaultValue: 'develop',                                                    description: 'IAM code branch'),
    string(name: 'TESTSUITE_REPO',   defaultValue: 'https://github.com/marcocaberletti/iam-robot-testsuite.git', description: 'Testsuite code repository'),
    string(name: 'TESTSUITE_BRANCH', defaultValue: 'develop',                                                    description: 'Testsuite code repository'),
    choice(name: 'CONTEXT',          choices:      'dev\nprod',                                                  description: 'Infrastructure'),
  ]),
  //  pipelineTriggers([cron('@daily')]),
])


def registry = ''
def approver = ''
def image_name = "indigoiam/iam-login-service"
def test_client_image_name = "indigoiam/iam-test-client"
def image_tag = ''
def test_client_image_tag = 'latest'

stage('build'){
  node('generic') {
    git branch: "${params.BRANCH}", url: "${params.REPO}"
    stash name: 'iam-code', useDefaultExcludes: false
  }

  node('maven') {
    dir('/iam'){
      unstash 'iam-code'
      sh "mvn clean package -U -Dmaven.test.failure.ignore '-Dtest=!%regex[.*NotificationConcurrentTests.*]' -DfailIfNoTests=false"

      junit '**/target/surefire-reports/TEST-*.xml'

      dir('iam-login-service/target') {
        stash name: 'iam-war', include: 'iam-login-service.war'
      }

      dir('iam-test-client/target') {
        stash name: 'iam-test-client-jar', include: 'iam-test-client.jar'
      }

      image_tag = sh (script: 'echo v`sh utils/print-pom-version.sh`', returnStdout: true).trim()
      registry = env.DOCKER_REGISTRY_HOST
    }
  }
}

stage('test'){
  parallel(
      'static analysis': {
        node('maven'){
          dir('/iam'){
            unstash 'iam-code'
            withSonarQubeEnv{ sh "mvn ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}" }
          }
        }
      },
      'coverage' : {
        node('maven'){
          dir('/iam'){
            unstash 'iam-code'
            sh "mvn cobertura:cobertura -Dmaven.test.failure.ignore '-Dtest=!%regex[.*NotificationConcurrentTests.*]' -DfailIfNoTests=false"

            publishHTML(target: [
              reportName           : 'Coverage Report',
              reportDir            : 'iam-login-service/target/site/cobertura/',
              reportFiles          : 'index.html',
              keepAll              : true,
              alwaysLinkToLastBuild: true,
              allowMissing         : false
            ])
          }
        }
      },
      'checkstyle' : {
        node('maven'){
          dir('/iam'){
            unstash 'iam-code'
            dir('iam-persistence') { sh "mvn clean install" }
            sh "mvn checkstyle:check -Dcheckstyle.config.location=google_checks.xml"

            step([$class: 'hudson.plugins.checkstyle.CheckStylePublisher',
              pattern: '**/checkstyle-result.xml',
              healty: '20',
              unHealty: '100'])
          }
        }
      }
      )
}


stage("Promote to QA?") {
  timeout(time: 60, unit: 'MINUTES'){
    approver = input(message: 'Promote to QA?', submitterParameter: 'approver')
  }
}


stage("Docker images") {

  parallel(
      "iam-login-service": {
        node('docker'){
          unstash 'iam-code'

          dir('iam-login-service/target') { unstash 'iam-war' }

          withEnv([
            "IAM_LOGIN_SERVICE_VERSION=${image_tag}"
          ]){
            dir('iam-login-service/docker'){
              sh "sh build-prod-image.sh"
              sh "sh push-prod-image.sh"
            }
          }
        }
      },

      "iam-test-client":{
        node('docker'){
          unstash 'iam-code'

          dir('iam-test-client/target') { unstash 'iam-test-client-jar' }

          dir('iam-test-client/docker'){
            sh "sh build-prod-image.sh"
            sh "sh push-prod-image.sh"
          }
        }
      }
      )
}

def iam_image = "${image_name}:${image_tag}-latest"
def test_client_image = "${test_client_image_name}:${test_client_image_tag}"

stage("Selenium testsuite"){
  def test_chrome
  def test_ff

  parallel(
      "develop-chrome": {
        test_chrome = build job: 'iam-deployment-test', propagate: false,
        parameters: [
          string(name: 'BRANCH',           value: "${params.BRANCH}"),
          string(name: 'BROWSER',          value: 'chrome'),
          string(name: 'IAM_IMAGE',        value: "${iam_image}"),
          string(name: 'TESTSUITE_REPO',   value: "${params.TESTSUITE_REPO}"),
          string(name: 'TESTSUITE_BRANCH', value: "${params.TESTSUITE_BRANCH}"),
        ]
      },
      "develop-firefox": {
        test_ff = build job: 'iam-deployment-test', propagate: false,
        parameters: [
          string(name: 'BRANCH',           value: "${params.BRANCH}"),
          string(name: 'BROWSER',          value: 'firefox'),
          string(name: 'IAM_IMAGE',        value: "${iam_image}"),
          string(name: 'TESTSUITE_REPO',   value: "${params.TESTSUITE_REPO}"),
          string(name: 'TESTSUITE_BRANCH', value: "${params.TESTSUITE_BRANCH}"),
        ]
      },
      )

  if("FAILURE".equals(test_chrome.result) && "FAILURE".equals(test_ff.result)) {
    currentBuild.result = 'FAILURE'
    sh "exit 1"
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

