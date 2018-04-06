#!/usr/bin/env groovy

def pkg_job

pipeline {
  agent none

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    string(name: 'PKG_TAG', defaultValue: 'v1.0.0', description: '')
  }

  environment {
    TITLE = "Indigo IAM Login Service"
    TARGET = "beta"
  }

  stages {
    stage('build pkgs'){
      steps{
        script {
          pkg_job = build job: 'iam_trigger.pkg.indigo-iam',
          parameters: [
            string(name: 'PKG_TAG', value: "${params.PKG_TAG}"),
            string(name: 'INCLUDE_BUILD_NUMBER', value: '0'),
          ]
        }
      }
    }

    stage('Promote to beta'){
      steps {
        script {
          build job: 'promote-packages',
          parameters: [
            string(name: 'PRODUCT', value: 'indigo-iam'),
            string(name: 'BUILD_NUMBER', value: "${pkg_job.number}"),
            string(name: 'TARGET', value: "${env.TARGET}"),
            string(name: 'REPO_TITLE', value: "${env.TITLE}")
          ]
        }
      }
    }

    stage('Publish to Nexus'){
      steps{
        script {
          build job: 'nexus-publisher',
          parameters: [
            string(name: 'PRODUCT', value: 'indigo-iam'),
            string(name: 'TARGET', value: "${env.TARGET}"),
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
