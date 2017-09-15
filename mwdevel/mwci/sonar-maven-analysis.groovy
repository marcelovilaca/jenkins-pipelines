#!/usr/bin/env groovy

def readProperty(filename, prop) {
  def value = sh script: "cat ${filename} | grep ${prop} | cut -d'=' -f2-", returnStdout: true
  return value.trim()
}

def jsonParse(url, basicAuth, field) {
  def value =  sh script: "curl -s -u '${basicAuth}' '${url}' | jq -r '${field}'", returnStdout: true
  return value.trim()
}

pipeline {
  agent none

  options {
    buildDiscarder(logRotator(numToKeepStr: '10'))
  }

  parameters {
    string(name: 'REPO',   defaultValue: '', description: 'Git code repository')
    string(name: 'BRANCH', defaultValue: '', description: 'Repository branch')
  }

  stages {
    stage('analysis') {
      agent { label 'maven' }
      steps {
        git url: "${params.REPO}", branch: "${params.BRANCH}"

        script {
          def cobertura_opts = 'cobertura:cobertura -Dmaven.test.failure.ignore -DfailIfNoTests=false -Dcobertura.report.format=xml'
          def checkstyle_opts = 'checkstyle:check -Dcheckstyle.config.location=google_checks.xml'

          withSonarQubeEnv{ sh "mvn clean -U ${cobertura_opts} ${checkstyle_opts} ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}" }
        }
        dir('target/sonar') {
          stash name: 'sonar-report', include: 'report-task.txt'
        }
      }
    }

    stage('quality gate'){
      agent { label 'generic' }
      steps {
        script {
          unstash 'sonar-report'

          def sonarServerUrl = readProperty('report-task.txt', 'serverUrl')
          def ceTaskUrl = readProperty('report-task.txt', 'ceTaskUrl')
          def sonarBasicAuth

          withSonarQubeEnv{ sonarBasicAuth  = "${SONAR_AUTH_TOKEN}:" }

          timeout(time: 3, unit: 'MINUTES') {
            waitUntil {
              def result = jsonParse(ceTaskUrl, sonarBasicAuth, '.task.status')
              echo "Current CeTask status: ${result}"
              return "SUCCESS" == "${result}"
            }
          }

          def analysisId = jsonParse(ceTaskUrl, sonarBasicAuth, '.task.analysisId')
          echo "Analysis ID: ${analysisId}"

          def url = "${sonarServerUrl}/api/qualitygates/project_status?analysisId=${analysisId}"
          def qualityGate =  jsonParse(url, sonarBasicAuth, '')
          echo "${qualityGate}"

          def status =  jsonParse(url, sonarBasicAuth, '.projectStatus.status')

          if ("ERROR" == "${status}") {
            currentBuild.result = 'UNSTABLE'
          }
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
        if('SUCCESS'.equals(currentBuild.result)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
