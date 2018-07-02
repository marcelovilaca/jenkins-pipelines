#!/usr/bin/env groovy

pipeline {
  agent { label 'maven' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    string(name: 'BRANCH', defaultValue: 'aarc-devel', description: '' )
  }

  stages {
    stage('prepare'){
      steps {
        container('maven-runner'){
          git branch: "${params.BRANCH}", url: 'https://github.com/rcauth-eu/security-lib'
          sh 'sed -i \'s#sonatype-nexus-staging#cnaf-releases#g\' pom.xml'
          sh 'sed -i \'s#sonatype-nexus-snapshots#cnaf-snapshots#g\' pom.xml'
          sh 'sed -i \'s#https:\\/\\/oss\\.sonatype\\.org\\/service\\/local\\/staging\\/deploy\\/maven2\\/#https:\\/\\/repo\\.cloud\\.cnaf\\.infn\\.it\\/repository\\/cnaf-releases#g\' pom.xml'
          sh 'sed -i \'s#https:\\/\\/oss\\.sonatype\\.org\\/content\\/repositories\\/snapshots\\/#https:\\/\\/repo\\.cloud\\.cnaf\\.infn\\.it\\/repository\\/cnaf-snapshots#g\' pom.xml'
        }
      }
    }

    stage('deploy'){
      steps {
        container('maven-runner'){
          sh "mvn clean -U -B deploy"
        }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }
    unstable {
      slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Unstable (<${env.BUILD_URL}|Open>)"
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
