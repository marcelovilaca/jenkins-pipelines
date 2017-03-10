#!groovy
// name: iam-build

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    string(name: 'BRANCH', defaultValue: 'develop', description: '' )
  ]),
  pipelineTriggers([cron('@daily')]),
])


stage("prepare"){
  node('generic') {
    git branch: "${params.BRANCH}", url: 'https://github.com/marcocaberletti/iam.git'
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
  parallel(
      'static analysis': {
        node('maven'){
          dir('/iam'){
            unstash 'code'
            withSonarQubeEnv{ sh "mvn ${SONAR_MAVEN_GOAL} -Dsonar.host.url=${SONAR_HOST_URL} -Dsonar.login=${SONAR_AUTH_TOKEN}" }
          }
        }
      },
      'coverage' : {
        node('maven'){
          dir('/iam'){
            unstash 'code'
            sh "mvn cobertura:cobertura -Dmaven.test.failure.ignore '-Dtest=!%regex[.*NotificationConcurrentTests.*]' -DfailIfNoTests=false"

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
            sh "mvn checkstyle:check -Dcheckstyle.config.location=google_checks.xml"

            step([$class: 'hudson.plugins.checkstyle.CheckStylePublisher',
              pattern: '**/checkstyle-result.xml',
              healty: '20',
              unHealty: '100'])
          }
        }
      }
      )
}