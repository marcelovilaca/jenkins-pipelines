#!/usr/bin/env groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '10')),
  parameters([
    string(name: 'REPO',           defaultValue: 'https://github.com/marcocaberletti/puppet'),
    string(name: 'BRANCH',         defaultValue: 'master'),
    string(name: 'PROJECTKEY',     defaultValue: 'puppet'),
    string(name: 'PROJECTNAME',    defaultValue: 'Puppet Modules'),
    string(name: 'PROJECTVERSION', defaultValue: '1.0'),
    string(name: 'SOURCES',        defaultValue: 'modules')
  ])
])


node('generic'){
  try {
    stage('analyze'){
      git url: "${params.REPO}", branch: "${params.BRANCH}"

      withSonarQubeEnv{
        def sonar_opts="-Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}"
        def project_opts="-Dsonar.projectKey=${params.PROJECTKEY} -Dsonar.projectName='${params.PROJECTNAME}' -Dsonar.projectVersion=${params.PROJECTVERSION} -Dsonar.sources=${params.SOURCES}"

        sh "/opt/sonar-scanner/bin/sonar-scanner ${sonar_opts} ${project_opts}"

        dir('.scannerwork') {
          stash name: 'sonar-report', include: 'report-task.txt'
        }
      }
    }

    stage('quality gate'){
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
        slackSend color: 'warning', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Unstable (<${env.BUILD_URL}|Open>)"
      }
    }
  }catch(e) {
    slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    throw(e)
  }
}


def readProperty(filename, prop) {
  def value = sh script: "cat ${filename} | grep ${prop} | cut -d'=' -f2-", returnStdout: true
  return value.trim()
}

def jsonParse(url, basicAuth, field) {
  def value =  sh script: "curl -s -u '${basicAuth}' '${url}' | jq -r '${field}'", returnStdout: true
  return value.trim()
}
