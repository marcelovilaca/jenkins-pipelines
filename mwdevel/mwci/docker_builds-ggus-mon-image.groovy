#!/usr/bin/env groovy

pipeline {
  agent { label 'docker' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') }

  stages {
    stage('prepare'){
      steps {
        deleteDir()
        git 'https://github.com/italiangrid/docker-scripts'
      }
    }

    stage('build'){
      steps {
        dir('ggus-mon-image'){ sh './build-image.sh' }
      }
    }

    stage('push'){
      steps {
        dir('ggus-mon-image'){ sh './push-image.sh' }
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
