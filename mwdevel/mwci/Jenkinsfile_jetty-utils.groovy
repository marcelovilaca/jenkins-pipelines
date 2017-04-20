#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'BRANCH', defaultValue: 'master', description: '' )
  ]),
])

node('maven'){
  stage('prepare'){
    git branch: "${params.BRANCH}", url: 'https://github.com/italiangrid/jetty-utils'
    sh 'sed -i \'s#http:\\/\\/radiohead\\.cnaf\\.infn\\.it:8081\\/nexus\\/content\\/repositories#https:\\/\\/repo\\.cloud\\.cnaf\\.infn\\.it\\/repository#g\' pom.xml'
  }

  stage('deploy'){ sh "mvn clean -U -B deploy" }
}
