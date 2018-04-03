#!/usr/bin/env groovy

pipeline {
  agent none

  options {
    buildDiscarder(logRotator(numToKeepStr: '5'))
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
        container('generic-runner'){
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
    }

    stage('Promote DEBs from Beta to Stable'){
      agent { label 'generic-ubuntu'}
      steps {
        container('ubuntu-runner'){   
          sh """
            cd ${env.PKG_ROOT}/${env.TARGET}/xenial
            dpkg-scanpackages -m amd64 | gzip > amd64/Packages.gz
          """
        }
      }
    }

    stage('Publish to Nexus'){
      steps{
        script{
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
