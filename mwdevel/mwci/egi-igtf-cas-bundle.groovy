pipeline {
  agent { label 'kubectl' }

  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    string(name: 'SECRET_NAME', defaultValue: 'egi-igtf-cas-secret', description: 'Kube secret name')
  }

  triggers { cron('@daily') }

  environment {
    OUTPUT_DIR = "/srv/scratch/${env.BUILD_TAG}/"
    POD_NAME = "egi-igtf-cas"
    POD_FILE = "egi-igtf-cas.pod.yaml"
  }

  stages {
    stage('build image'){
      agent {label 'docker'}
      steps{
        deleteDir()
        git 'https://github.com/marcocaberletti/docker'
        dir('egi-igtf-cas') {
          sh "./build-image.sh"
          sh "./push-image.sh"
        }
      }
    }


    stage('prepare'){
      steps {
        deleteDir()
        sh "mkdir -p ${env.OUTPUT_DIR}"

        script {
          def pod_template = """
apiVersion: v1
kind: Pod
metadata:
  name: ${env.POD_NAME}
  namespace: default
spec:
  nodeSelector:
    role: worker
  restartPolicy: Never
  volumes:
  - name: scratch-area
    nfs:
      server: 10.0.0.30
      path: /srv/kubernetes/volumes/scratch
  containers:
  - name: egi-igtf-cas
    image: ${env.DOCKER_REGISTRY_HOST}/italiangrid/egi-igtf-cas:latest
    volumeMounts:
    - name: scratch-area
      mountPath: /srv/scratch
    env:
    - name: BUNDLE_DEST_DIR
      value: ${OUTPUT_DIR}
    - name: TZ
      value: Europe/Rome
"""
          writeFile file: "${env.POD_FILE}", text: "${pod_template}"
        }
      }
    }

    stage('run'){
      steps {
        sh "kubectl apply -f ${env.POD_FILE}"
        sh "while ( [ 'Running' != `kubectl get pod ${env.POD_NAME} -o jsonpath='{.status.phase}'` ] ); do echo 'Waiting pod...'; sleep 5; done"

        sh "kubectl logs -f ${env.POD_NAME}"
      }

      post {
        always {   sh "kubectl delete -f ${env.POD_FILE}" }
      }
    }

    stage('update secret'){
      steps {
        sh """
          kubectl create secret generic ${params.SECRET_NAME} \\
            --namespace=default --from-file=ca.crt=${env.OUTPUT_DIR}/tls-ca-bundle.pem \\
            --dry-run -o yaml | \\
          kubectl replace secret ${params.SECRET_NAME} -f -
          """
        script { currentBuild.result = 'SUCCESS' }
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script{
        if('SUCCESS'.equals(currentBuild.result)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
