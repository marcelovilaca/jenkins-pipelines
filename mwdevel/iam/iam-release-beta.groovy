#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  parameters([
    string(name: 'PKG_TAG', defaultValue: 'master', description: ''),
  ]),
])


def pkg_job, approver
def title = 'Indigo IAM Login Service'
def target = 'beta'

try {
  stage('build packages'){
    pkg_job = build job: 'iam_trigger.pkg.indigo-iam',
    parameters: [
      string(name: 'PKG_TAG', value: "${params.PKG_TAG}"),
      string(name: 'INCLUDE_BUILD_NUMBER', value: '0'),
    ]
  }

  stage("Promote to Beta") {
    slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Requires approval to the next stage (<${env.BUILD_URL}|Open>)"

    timeout(time: 60, unit: 'MINUTES'){
      approver = input(message: 'Promote Packages to BETA release?', submitterParameter: 'approver')
    }

    build job: 'promote-packages',
    parameters: [
      string(name: 'PRODUCT', value: 'indigo-iam'),
      string(name: 'BUILD_NUMBER', value: "${pkg_job.number}"),
      string(name: 'TARGET', value: "${target}"),
      string(name: 'REPO_TITLE', value: "${title}")
    ]
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}
