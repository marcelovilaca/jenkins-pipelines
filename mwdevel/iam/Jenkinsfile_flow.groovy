#!groovy
// name: iam-workflow

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    string(name: 'BRANCH', defaultValue: 'develop', description: '')
  ]),
  //  pipelineTriggers([cron('@daily')]),
])

def approver = ''
def image_name = "indigoiam/iam-login-service"
def image_tag = ''

stage("DEV build") {
  build job: 'iam-build', parameters: [
    string(name: 'BRANCH', value: "${params.BRANCH}")
  ]
}

stage("Promote to QA?") {
  timeout(time: 60, unit: 'MINUTES'){
    approver = input(message: 'Promote to QA?', submitterParameter: 'approver')
  }
}


stage("QA tests") {
  node('generic'){
    step ([$class: 'CopyArtifact',
      projectName: 'iam-build',
      filter: 'version.txt'])
    image_tag = readFile('version.txt').trim()
  }

  build job: 'docker_build-iam-image', parameters: [
    string(name: 'IMAGE_NAME', value: "${image_name}"),
    string(name: 'TAG', value: "${image_tag}")
  ]

  step('deployment tests'){
    parallel(
        "develop-chrome": {
          build job: 'iam-deployment-test',
          parameters: [
            string(name: 'BRANCH', value: '${params.BRANCH}'),
            string(name: 'BROWSER', value: 'chrome'),
            string(name: 'IAM_IMAGE', value: "${image_name}:${image_tag}"),
          ]
        },
        "develop-firefox": {
          build job: 'iam-deployment-test',
          parameters: [
            string(name: 'BRANCH', value: '${params.BRANCH}'),
            string(name: 'BROWSER', value: 'firefox'),
            string(name: 'IAM_IMAGE', value: "${image_name}:${image_tag}"),
          ]
        },
        )
  }

}


stage("Promotion to staging") {

  timeout(time: 60, unit: 'MINUTES'){
    approver = input(message: 'Promote to STAGING?', submitterParameter: 'approver')
  }
}


stage("Deploy to staging") {

  build job: 'iam-kube-deploy', parameters: [
    string(name: 'ENVIRONMENT', value: 'STAGING'),
    string(name: 'IAM_IMAGE', value: "${image_name}:${image_tag}")
  ]
}


stage("Promotion to production") {

  timeout(time: 60, unit: 'MINUTES'){
    approver = input(message: 'Promote to PRODUCTION?', submitterParameter: 'approver')
  }
}


stage("Production") {

  build job: 'iam-kube-deploy', parameters: [
    string(name: 'ENVIRONMENT', value: 'PRODUCTION'),
    string(name: 'IAM_IMAGE', value: "${image_name}:${image_tag}")
  ]
}

