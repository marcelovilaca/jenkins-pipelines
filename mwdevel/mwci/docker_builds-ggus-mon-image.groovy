#!/usr/bin/env groovy

pipeline {

  agent {
      kubernetes {
          label "${env.JOB_NAME}-${env.BUILD_NUMBER}"
          cloud 'Kube mwdevel'
          defaultContainer 'jnlp'
          inheritFrom 'ci-template'
      }
  }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  environment {
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
  }

  stages {
    stage('prepare'){
      steps {
        container('runner'){
          deleteDir()
          git 'https://github.com/italiangrid/docker-scripts'
        }
      }
    }

    stage('build'){
      steps {
        container('runner'){
          dir('ggus-mon-image'){ 
            sh './build-image.sh' 
          }
        }
      }
    }

    stage('push'){
      steps {
        container('runner'){
          dir('ggus-mon-image'){ 
            sh './push-image.sh' 
          }
        }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script{
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
