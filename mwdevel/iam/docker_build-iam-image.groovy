#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  parameters([
    string(name: 'IAM_REPO',                 defaultValue: 'https://github.com/indigo-iam/iam.git', description: '' ),
    string(name: 'IAM_BRANCH',               defaultValue: 'develop', description: '' ),
    string(name: 'LOGIN_SERVICE_IMAGE_NAME', defaultValue: 'indigoiam/iam-login-service', description: ''),
    string(name: 'TEST_CLIENT_IMAGE_NAME',   defaultValue: 'indigoiam/iam-test-client', description: ''),
    string(name: 'ARTIFACT_FROM_BUILD',      defaultValue: '', description: 'Build number from which get the artifact. Empty for last stable build.'),
    string(name: 'LOGIN_SERVICE_VERSION',    defaultValue: 'develop', description: '')
  ]),
  pipelineTriggers([cron('@daily')]),
])


def from_build_number
def pom_version

try {
  stage('prepare'){
    node('generic'){
      git branch: "${params.IAM_BRANCH}", url: "${params.IAM_REPO}"

      if("" == "${params.ARTIFACT_FROM_BUILD}" ) {
        from_build_number = sh returnStdout: true, script: 'curl -sk http://jenkinsci.default.svc/job/iam-build/lastSuccessfulBuild/buildNumber'
        echo "Last stable build: ${from_build_number}"
      }else{
        from_build_number = "${params.ARTIFACT_FROM_BUILD}"
        echo "Parameter build: ${from_build_number}"
      }


      if("" == "${params.LOGIN_SERVICE_VERSION}") {
        step ([$class: 'CopyArtifact',
          projectName: 'iam-build',
          filter: 'version.txt',
          selector: [$class: 'SpecificBuildSelector', buildNumber: "${from_build_number}"]
        ])
        pom_version = readFile 'version.txt'
        pom_version = pom_version.trim()
      }else{
        pom_version = "${params.LOGIN_SERVICE_VERSION}"
      }

      dir('iam-login-service/target') {
        step ([$class: 'CopyArtifact',
          projectName: 'iam-build',
          filter: 'iam-login-service.war',
          selector: [$class: 'SpecificBuildSelector', buildNumber: "${from_build_number}"]
        ])
      }

      dir('iam-test-client/target') {
        step ([$class: 'CopyArtifact',
          projectName: 'iam-build',
          filter: 'iam-test-client.jar',
          selector: [$class: 'SpecificBuildSelector', buildNumber: "${from_build_number}"]
        ])
      }

      stash name: 'iam-code', useDefaultExcludes: false
    }
  }

  stage('build'){
    parallel(
        "iam-login-service": {
          node('docker'){
            dir('login-service'){
              unstash 'iam-code'

              withEnv([
                "IAM_LOGIN_SERVICE_IMAGE=${params.LOGIN_SERVICE_IMAGE_NAME}",
                "IAM_LOGIN_SERVICE_VERSION=${pom_version}",
              ]){
                dir('iam-login-service/docker'){
                  sh "sh build-prod-image.sh"
                  sh "sh push-prod-image.sh"
                }
              }
            }
          }
        },

        "iam-test-client":{
          node('docker'){
            dir('test-client'){
              unstash 'iam-code'

              withEnv([
                "IAM_TEST_CLIENT_IMAGE=${params.TEST_CLIENT_IMAGE_NAME}"
              ]) {
                dir('iam-test-client/docker'){
                  sh "sh build-prod-image.sh"
                  sh "sh push-prod-image.sh"
                }
              }
            }
          }
        }
        )
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}
