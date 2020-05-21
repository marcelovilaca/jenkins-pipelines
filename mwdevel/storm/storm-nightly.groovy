#!/usr/bin/env groovy

pipeline {
  agent {
    label 'docker'
  }
	
  options {
    timestamps()
    ansiColor('xterm')
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('@daily') } 
    
  stages {
      
    stage('build packages'){
      steps{
        build 'pkg.storm/nightly'
      }
    }
      
    stage('update nightly repos'){
      steps {
        build 'release.storm/nightly'
      }
    }
  }

  post {
    failure {
      slackSend channel: '#storm', color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }
    
    changed {
      script{
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend channel: '#storm', color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
