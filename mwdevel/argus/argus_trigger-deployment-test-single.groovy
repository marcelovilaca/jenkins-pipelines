#!/usr/bin/env groovy

def test_el6, test_el7

pipeline {
  agent none

  options {
    timeout(time: 3, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    choice (name: 'REPO', choices: 'ci\nbeta\nstable', description: 'Repository where download Argus RPMs.')
    choice (name: 'GH_REPO', choices: 'staging\nproduction', description: 'Github repository holding Argus RPMs.')
    string (name: 'TESTSUITE_REPO', defaultValue: 'https://github.com/argus-authz/argus-robot-testsuite', description: '' )
    string (name: 'TESTSUITE_BRANCH', defaultValue: 'master', description: '')
  }

  stages {
    stage('run centos6'){
      steps{
        script {
          test_el6 = build job: 'argus-deployment-test-single',
          propagate: false,
          parameters: [
            string(name: 'PLATFORM', value: 'centos6'),
            string(name: 'TESTSUITE_REPO', value: "${params.TESTSUITE_REPO}"),
            string(name: 'TESTSUITE_BRANCH', value: "${params.TESTSUITE_BRANCH}"),
            string(name: 'REPO', value: "${params.REPO}"),
            string(name: 'GH_REPO', value: "${params.GH_REPO}"),
          ]
          
          if("FAILURE".equals(test_el6.result)) {
            currentBuild.result = 'UNSTABLE'
          }
        }
      }
    }

    stage('run centos7'){
      steps{
        script {
          test_el7 = build job: 'argus-deployment-test-single',
          propagate: false,
          parameters: [
            string(name: 'PLATFORM', value: 'centos7'),
            string(name: 'TESTSUITE_REPO', value: "${params.TESTSUITE_REPO}"),
            string(name: 'TESTSUITE_BRANCH', value: "${params.TESTSUITE_BRANCH}"),
            string(name: 'REPO', value: "${params.REPO}"),
            string(name: 'GH_REPO', value: "${params.GH_REPO}"),
          ]
          
          if("FAILURE".equals(test_el6.result)) {
            currentBuild.result = 'UNSTABLE'
          }
        }
      }
    }

    stage('result'){
      steps {
        script {
          if("FAILURE".equals(test_el6.result) && "FAILURE".equals(test_el7.result)) {
            currentBuild.result = 'FAILURE'
            sh "exit 1"
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
