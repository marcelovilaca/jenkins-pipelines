#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  parameters([
    string(name: 'PKG_TAG', defaultValue: 'release/1.7.2', description: ''),
    string(name: 'COMPONENT_LIST', defaultValue: '', description: 'Components to build' ),
    string(name: 'GITHUB_REPO', defaultValue: 'github.com/marcocaberletti/repo', description: '')
  ]),
])


def rpm_job, approver
def title = 'Argus Authorization Service'
def target = 'beta'

try {
  stage('build RPMs'){
    rpm_job = build job: 'argus_trigger.pkg.argus',
    parameters: [
      string(name: 'PKG_TAG', value: "${params.PKG_TAG}"),
      string(name: 'COMPONENT_LIST', value: "${params.COMPONENT_LIST}"),
      string(name: 'INCLUDE_PKG_BUILD_NUMBER', value: '0'),
    ]
  }


  stage("Promote to Beta") {
    slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Requires approval to the next stage (<${env.BUILD_URL}|Open>)"

    timeout(time: 60, unit: 'MINUTES'){
      approver = input(message: 'Promote Packages to BETA release?', submitterParameter: 'approver')
    }

    build job: 'promote-packages',
    parameters: [
      string(name: 'PRODUCT', value: 'argus'),
      string(name: 'BUILD_NUMBER', value: "${rpm_job.number}"),
      string(name: 'TARGET', value: "${target}"),
      string(name: 'REPO_TITLE', value: "${title}")
    ]
  }


  stage("Publish on GitHub"){
    slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Requires approval to the next stage (<${env.BUILD_URL}|Open>)"

    timeout(time: 60, unit: 'MINUTES'){
      approver = input(message: 'Push packages to GitHub repo?', submitterParameter: 'approver')
    }

    node('generic'){
      def github_repo_url = "https://${params.GITHUB_REPO}"
      def github_repo_branch = "gh-pages"

      dir('repo'){
        git url: "${github_repo_url}", branch: "${github_repo_branch}"

        sh "rsync -avu --delete /mnt/packages/repo/argus/${target}/ ${target}/"

        dir("${target}/") {
          sh "createrepo el6/RPMS"
          sh "repoview -t '${title} (CentOS 6)' el6/RPMS"

          sh "createrepo el7/RPMS"
          sh "repoview -t '${title} (CentOS 7)' el7/RPMS"
        }

        withCredentials([
          usernamePassword(credentialsId: 'marco-github-credentials', passwordVariable: 'git_password', usernameVariable: 'git_username')
        ]) {
          sh "git config --global user.name 'JenkinsCI'"
          sh "git config --global user.email 'jenkinsci@cloud.cnaf.infn.it'"
          sh "git add ."
          sh "git commit -m 'Upload ${target} packages for ${params.PKG_TAG}'"
          sh "git push https://${git_username}:${git_password}@${params.GITHUB_REPO}"
        }
      }
    }
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}
