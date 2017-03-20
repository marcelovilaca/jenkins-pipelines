#!groovy
// name: iam-build

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
  parameters([
    string(name: 'REPO',   defaultValue: 'https://github.com/marcocaberletti/iam.git', description: '' ),
    string(name: 'BRANCH', defaultValue: 'develop', description: '' )
  ]),
])


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
      sh "echo v`sh utils/print-pom-version.sh`-latest > version.txt"
      sh "git rev-parse --short HEAD > version-commit.txt"
      sh "mvn clean package -U -Dmaven.test.failure.ignore '-Dtest=!%regex[.*NotificationConcurrentTests.*]' -DfailIfNoTests=false"

      junit '**/target/surefire-reports/TEST-*.xml'
      archive 'iam-login-service/target/iam-login-service.war'
      archive 'docker/saml-idp/idp/shibboleth-idp/metadata/idp-metadata.xml'
      archive 'version.txt'
      archive 'version-commit.txt'
    }
  }
}

stage('test'){

  def cobertura_opts = 'cobertura:cobertura -Dmaven.test.failure.ignore \'-Dtest=!%regex[.*NotificationConcurrentTests.*]\' -DfailIfNoTests=false'
  def checkstyle_opts = 'checkstyle:check -Dcheckstyle.config.location=google_checks.xml'

  parallel(

      'static analysis': {
        node('maven'){
          dir('/iam'){
            unstash 'code'
            withSonarQubeEnv{ sh "mvn ${cobertura_opts} ${checkstyle_opts} ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}" }
          }
        }
      },

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
      }
      )
}