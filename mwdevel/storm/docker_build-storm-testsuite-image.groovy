#!/usr/bin/env groovy

pipeline {
  agent { label 'docker' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  environment {
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
    DIRECTORY = "storm-testsuite-image"
  }

  stages {
    stage('prepare'){
      steps {
        container('docker-runner'){
          deleteDir()
          git 'https://github.com/italiangrid/docker-scripts'
        }
      }
    }

    stage('build'){
      steps {
        container('docker-runner'){
          dir("${env.DIRECTORY}"){ 
            sh 'sh build-image.sh' 
          }
        }
      }
    }

    stage('push'){
      steps {
        container('docker-runner'){
          dir("${env.DIRECTORY}"){ 
            sh "docker tag italiangrid/storm-testsuite ${DOCKER_REGISTRY_HOST}/italiangrid/storm-testsuite"
			sh "docker push ${DOCKER_REGISTRY_HOST}/italiangrid/storm-testsuite"
          }
        }
      }
    }
    
    stage('result'){
      steps {
        script { currentBuild.result = 'SUCCESS' }
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
