#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'REPO',   defaultValue: 'https://github.com/marcocaberletti/iam.git', description: '' ),
    string(name: 'BRANCH', defaultValue: 'develop', description: '' )
  ]),
])

try {
  stage("prepare"){
    node('generic') {
      git branch: "${params.BRANCH}", url: "${params.REPO}"
      stash name: 'code', useDefaultExcludes: false
    }
  }

  stage('build'){
    node('maven') {
      dir('/iam'){
        unstash 'code'
        sh "echo v`sh utils/print-pom-version.sh` > version.txt"
        sh "git rev-parse --short HEAD > version-commit.txt"
        sh "mvn clean package -U -Dmaven.test.failure.ignore '-Dtest=!%regex[.*NotificationConcurrentTests.*]' -DfailIfNoTests=false"

        junit '**/target/surefire-reports/TEST-*.xml'
        dir('iam-login-service/target') { archive 'iam-login-service.war' }
        dir('iam-test-client/target') { archive 'iam-test-client.jar' }
        dir('docker/saml-idp/idp/shibboleth-idp/metadata'){ archive 'idp-metadata.xml' }
        archive 'version.txt'
        archive 'version-commit.txt'
      }
    }
  }

  stage('code analysis'){
    def cobertura_opts = 'cobertura:cobertura -Dmaven.test.failure.ignore \'-Dtest=!%regex[.*NotificationConcurrentTests.*]\' -DfailIfNoTests=false'
    def checkstyle_opts = 'checkstyle:check -Dcheckstyle.config.location=google_checks.xml'
    def sonar_job

    parallel(
        'coverage' : {
          node('maven'){
            dir('/iam'){
              unstash 'code'
              sh "mvn ${cobertura_opts}"

              publishHTML(target: [
                reportName           : 'Coverage Report',
                reportDir            : 'iam-login-service/target/site/cobertura/',
                reportFiles          : 'index.html',
                keepAll              : true,
                alwaysLinkToLastBuild: true,
                allowMissing         : false
              ])
            }
          }
        },
        'checkstyle' : {
          node('maven'){
            dir('/iam'){
              unstash 'code'
              dir('iam-persistence') { sh "mvn clean install" }
              sh "mvn ${checkstyle_opts}"

              step([$class: 'hudson.plugins.checkstyle.CheckStylePublisher',
                pattern: '**/checkstyle-result.xml',
                healty: '20',
                unHealty: '100'])
            }
          }
        },
        'static analysis': {
          sonar_job = build job: 'sonar-maven-analysis', propagate: false,
          parameters: [
            string(name: 'REPO',   value: "${params.REPO}"),
            string(name: 'BRANCH', value: "${params.BRANCH}"),
          ]
        },
        )

    currentBuild.result = sonar_job.result
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}