#!groovy
// name: docker_build-iam-ts-images

properties([
  buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '5')),
  parameters([
    string(name: 'BRANCH',    defaultValue: 'develop', description: '' ),
  ]),
  pipelineTriggers([cron('@daily')]),
])


stage('prepare code'){
  node('generic'){
    sh "git clone https://github.com/marcocaberletti/iam-deployment-test.git iam-nginx"
    dir('iam-nginx/iam/nginx'){
      stash include: './*', name: 'iam-nginx'
    }

    sh "git clone https://github.com/marcocaberletti/docker.git docker-images"
    dir('docker-images/trust-anchors'){
      stash include: './*', name: 'trust-anchors'
    }

    sh "git clone -b ${params.BRANCH} https://github.com/marcocaberletti/iam-robot-testsuite.git iam-ts"
    dir('iam-ts/docker'){
      stash include: './*', name: 'iam-ts'
    }
  }
}

stage('create images'){
  parallel(
      "iam-nginx": {
        node('docker'){
          unstash 'iam-nginx'
          sh "docker build --no-cache -t italiangrid/iam-nginx:latest ."
          sh "docker tag italiangrid/iam-nginx:latest ${DOCKER_REGISTRY_HOST}/italiangrid/iam-nginx:latest"
          sh "docker push ${DOCKER_REGISTRY_HOST}/italiangrid/iam-nginx:latest"
        }
      },

      "trust-anchors": {
        node('docker'){
          unstash 'trust-anchors'
          sh './build-image.sh'
          sh './push-image.sh'
        }
      },

      "iam-testsuite": {
        node('docker'){
          unstash 'iam-ts'
          sh './build-image.sh'
          sh './push-image.sh'
        }
      }
      )
}



