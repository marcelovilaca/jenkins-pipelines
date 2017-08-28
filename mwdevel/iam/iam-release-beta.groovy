#!/usr/bin/env groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  parameters([
    string(name: 'PKG_TAG', defaultValue: 'v1.0.0', description: ''),
    string(name: 'GITHUB_REPO', defaultValue: 'github.com/marcocaberletti/test-lfs-repo', description: '')
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

  stage("Publish to Github"){
    slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Requires approval to the next stage (<${env.BUILD_URL}|Open>)"

    timeout(time: 60, unit: 'MINUTES'){
      approver = input(message: 'Push packages to GitHub repo?', submitterParameter: 'approver')
    }

    node('generic'){
      def github_repo_url = "https://${params.GITHUB_REPO}"
      def github_repo_branch = "master"

      dir('repo'){
        sh "git clone -b ${github_repo_branch} ${github_repo_branch} ."
        sh "rsync -avu --delete /mnt/packages/repo/indigo-iam/${target}/ ${target}/"

        withCredentials([
          usernamePassword(credentialsId: 'marco-github-credentials', passwordVariable: 'git_password', usernameVariable: 'git_username')
        ]) {
          sh "git config --global user.name 'JenkinsCI'"
          sh "git config --global user.email 'jenkinsci@cloud.cnaf.infn.it'"
          sh "git add ."
          sh "git commit -m 'Upload ${target} packages for ${params.PKG_TAG}'"
          sh "git lfs push https://${git_username}:${git_password}@${params.GITHUB_REPO}"
        }
      }
    }
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}
