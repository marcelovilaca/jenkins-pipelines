#!/usr/bin/env groovy
@Library('sd')_
def kubeLabel = getKubeLabel()

pipeline{

  agent {
      kubernetes {
          label "${kubeLabel}"
          cloud 'Kube mwdevel'
          defaultContainer 'runner'
          inheritFrom 'ci-template'
      }
  }

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    choice(name: 'PRODUCT', choices: 'argus\nindigo-iam', description: 'Product packages')
    string(name: 'GH_REPO', defaultValue: '', description: 'GitHub repo, owner/repo. Ex.: argus-authz/repo' )
    string(name: 'GH_REPO_BRANCH', defaultValue: 'master', description: 'GitHub repo branch')
    choice(name: 'TARGET', choices: 'beta\nstable', description: 'Release target')
    string(name: 'COMMIT_MSG', defaultValue: '', description: 'Commit message')
  }

  stages {
    stage('push'){
      steps {
        dir('repo'){
          sh "git lfs clone -b ${params.GH_REPO_BRANCH} https://github.com/${params.GH_REPO} ."
          sh "rsync -av --delete /mnt/packages/repo/${params.PRODUCT}/${params.TARGET}/ ${params.TARGET}/"

          withCredentials([
            usernamePassword(credentialsId: 'marco-github-credentials', passwordVariable: 'git_password', usernameVariable: 'git_username')
          ]) {
            sh "git config --global user.name 'JenkinsCI'"
            sh "git config --global user.email 'jenkinsci@cloud.cnaf.infn.it'"
            sh 'git lfs track "*.rpm"'
            sh 'git lfs track "*.deb"'

            sh "git add ."
            sh "git commit -m '${params.COMMIT_MSG}'"
            sh "git push https://${git_username}:${git_password}@github.com/${params.GH_REPO}"
          }
        }
      }
    }

    stage('result'){
      steps {
        script{ currentBuild.result='SUCCESS' }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script{
        if('SUCCESS'.equals(currentBuild.result)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
