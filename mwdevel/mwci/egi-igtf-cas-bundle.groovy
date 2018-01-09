#!/usr/bin/env groovy

pipeline {
  agent { label 'kubectl' }

  options {
    timeout(time: 2, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  parameters {
    string(name: 'SECRET_NAME', defaultValue: 'egi-igtf-cas-secret', description: 'Kube secret name')
  }

  triggers { cron('@daily') }

  environment {
    OUTPUT_DIR = "/srv/scratch/${env.BUILD_TAG}"
    POD_NAME = "egi-igtf-cas"
    POD_FILE = "egi-igtf-cas.pod.yaml"
    DOCKER_REGISTRY_HOST = "${env.DOCKER_REGISTRY_HOST}"
  }

  stages {
    stage('build image'){
      agent { label 'docker' }
      steps{
        container('docker-runner'){
          deleteDir()
          git 'https://github.com/marcocaberletti/docker'
          dir('egi-igtf-cas') {
            sh "./build-image.sh"
            sh "./push-image.sh"
          }
        }
      }
    }

    stage('prepare'){
      steps {
        container('kubectl-runner'){
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
    persistentVolumeClaim:
      claimName: scratch-area-claim
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
            writeFile file: "${env.OUTPUT_DIR}/${env.POD_FILE}", text: "${pod_template}"
          }
        }
      }
    }

    stage('run'){
      steps {
        container('kubectl-runner'){
          sh "kubectl apply -f ${env.OUTPUT_DIR}/${env.POD_FILE}"
          sh "while ( [ 'Running' != `kubectl get pod ${env.POD_NAME} -o jsonpath='{.status.phase}'` ] ); do echo 'Waiting pod...'; sleep 1; done"

          sh "kubectl logs -f ${env.POD_NAME}"
        }
      }

      post { 
        always { 
          container('kubectl-runner'){
            sh "kubectl delete -f ${env.OUTPUT_DIR}/${env.POD_FILE}"
          }
        } 
      }
    }

    stage('update secret'){
      steps {
        container('kubectl-runner'){
          sh """
          kubectl create secret generic ${params.SECRET_NAME} \\
            --namespace=default --from-file=ca.crt=${env.OUTPUT_DIR}/tls-ca-bundle.pem \\
            --dry-run -o yaml > ${env.OUTPUT_DIR}/${params.SECRET_NAME}.secret.yaml
          kubectl --namespace=default delete -f ${env.OUTPUT_DIR}/${params.SECRET_NAME}.secret.yaml --ignore-not-found=true
          kubectl --namespace=default create -f ${env.OUTPUT_DIR}/${params.SECRET_NAME}.secret.yaml
          """
        }
      }
    }

    stage('archive'){
      steps {
        dir("${env.OUTPUT_DIR}"){
          archiveArtifacts 'tls-ca-bundle.pem'
          archiveArtifacts '*.yaml' 
        }
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
