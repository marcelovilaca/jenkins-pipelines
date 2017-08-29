#!/usr/bin/env groovy

def approver

pipeline {
  agent none

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    string(name: 'GITHUB_REPO', defaultValue: 'marcocaberletti/test-lfs-repo', description: 'GitHub repo, owner/repo')
    string(name: 'COMMIT_MSG', defaultValue: 'Promote packages from beta to stable', description: 'Commit message')
  }

  environment {
    TITLE = "Indigo IAM Login Service (CentOS 7)"
    TARGET = "stable"
    PKG_ROOT = "/mnt/packages/repo/indigo-iam"
  }

  stages {
    stage('Promote RPMs from Beta to Stable'){
      agent { label 'generic' }
      steps {
        dir("${env.PKG_ROOT}"){
          sh "rsync -avu beta/ ${env.TARGET}/"

          sh """
            cd ${env.TARGET}/
            createrepo el7/RPMS
            repoview -t '${env.TITLE}' el7/RPMS
          """
        }
      }
    }

    stage('Promote DEBs from Beta to Stable'){
      agent { label 'generic-ubuntu'}
      steps {   sh """
          cd ${env.PKG_ROOT}/${env.TARGET}/xenial
          dpkg-scanpackages -m amd64 | gzip > amd64/Packages.gz
        """   }
    }

    stage('Publish to Github'){
      steps{
        slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Requires approval to the next stage (<${env.BUILD_URL}|Open>)"
        script{
          timeout(time: 60, unit: 'MINUTES'){
            approver = input(message: 'Push packages to GitHub repo?', submitterParameter: 'approver')
          }

          build job: 'github-publisher',
          parameters: [
            string(name: 'PRODUCT', value: 'indigo-iam'),
            string(name: 'GH_REPO', value: "${params.GITHUB_REPO}"),
            string(name: 'GH_REPO_BRANCH', value: 'master'),
            string(name: 'TARGET', value: "${env.TARGET}"),
            string(name: 'COMMIT_MSG', value: "${params.COMMIT_MSG}"),
          ]
        }
      }
    }
  }

  post {
    success {
      slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Success (<${env.BUILD_URL}|Open>)"
    }
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }
  }
}
