#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'REPO',   defaultValue: 'https://github.com/marcocaberletti/iam.git', description: '' ),
    string(name: 'BRANCH', defaultValue: 'mail-tests', description: '' )
  ]),
])

try {
  stage('build'){
    node('maven') {
      git url: "${params.REPO}", branch: "${params.BRANCH}"
      sh "echo v`sh utils/print-pom-version.sh` > version.txt"
      sh "git rev-parse --short HEAD > version-commit.txt"
      sh "mvn clean package -U -Dmaven.test.failure.ignore"

      junit '**/target/surefire-reports/TEST-*.xml'
      dir('iam-login-service/target') { archive 'iam-login-service.war' }
      dir('iam-test-client/target') { archive 'iam-test-client.jar' }
      dir('docker/saml-idp/idp/shibboleth-idp/metadata'){ archive 'idp-metadata.xml' }
      archive 'version.txt'
      archive 'version-commit.txt'
    }
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}