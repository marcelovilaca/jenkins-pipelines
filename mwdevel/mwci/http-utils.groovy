#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'BRANCH', defaultValue: 'jetty9', description: '' )
  ]),
])

node('maven'){
  stage('prepare'){
    git branch: "${params.BRANCH}", url: 'https://github.com/italiangrid/https-utils'
    sh 'sed -i \'s#http:\\/\\/radiohead\\.cnaf\\.infn\\.it:8081\\/nexus\\/content\\/repositories#https:\\/\\/repo\\.cloud\\.cnaf\\.infn\\.it\\/repository#g\' pom.xml'
  }

  stage('analysis'){
    def cobertura_opts = 'cobertura:cobertura -Dmaven.test.failure.ignore -DfailIfNoTests=false -Dcobertura.report.format=xml'
    def checkstyle_opts = 'checkstyle:check -Dcheckstyle.config.location=google_checks.xml'

    withSonarQubeEnv{ sh "mvn clean -U ${cobertura_opts} ${checkstyle_opts} ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}" }
  }

  stage('deploy'){ sh "mvn clean -U -B deploy" }
}
