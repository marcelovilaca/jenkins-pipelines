#!groovy

properties([
  buildDiscarder(logRotator(numToKeepStr: '5')),
  pipelineTriggers([cron('@daily')]),
])

try {
  stage('get code'){
    node('generic'){
      git 'https://github.com/marcocaberletti/argus-deployment-test.git'
      stash name: "source", include: "./*"
    }
  }

  stage('create Docker images'){
    parallel(
        "centos6-allinone"   : { build_image('centos6', 'all-in-one') },
        "centos6-distributed": { build_image('centos6', 'distributed') },
        "centos7-allinone"   : { build_image('centos7', 'all-in-one') },
        "centos7-distributed": { build_image('centos7', 'distributed') },
        )
  }
}catch(e) {
  slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
  throw(e)
}

def build_image(platform, deployment){
  node('docker'){
    unstash "source"

    dir(deployment){
      withEnv(["PLATFORM=${platform}"]){
        sh "./build-images.sh"
        sh "./push-images.sh"
      }
    }
  }
}
