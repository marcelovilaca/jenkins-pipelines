#!/usr/bin/env groovy
@Library('sd')_
def kubeLabel = getKubeLabel()

pipeline {
  agent {
      kubernetes {
          label "${kubeLabel}"
          cloud 'Kube mwdevel'
          defaultContainer 'runner'
          inheritFrom 'ci-template'
      }
  }
  
  options {
    timeout(time: 1, unit: 'HOURS')
    buildDiscarder(logRotator(numToKeepStr: '5'))
  }

  triggers { cron('H/20 * * * *') }

  environment {
    REPORT_DIR = "/srv/scratch/${env.BUILD_TAG}/reports"
    POD_NAME = "ggus-report-${env.BUILD_NUMBER}"
    POD_FILE = "ggus-report.pod.yaml"
  }

  stages {
    stage('prepare'){
      steps {
          sh "mkdir -p ${env.REPORT_DIR}"

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
  - name: ggus-report-${env.BUILD_NUMBER}
    image: italiangrid/ggus-mon:latest
    volumeMounts:
    - name: scratch-area
      mountPath: /srv/scratch
    env:
    - name: REPORT_DIR
      value: ${REPORT_DIR}
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
	    always { 
	        sh "kubectl delete -f ${env.POD_FILE}"
	    }  
	  }
	}

    stage('archive & clean'){
      steps {
      	  sh "cp -rv ${env.REPORT_DIR} ."
	      dir("reports"){ 
	        archiveArtifacts "**" 
	      }
	      sh "rm -rfv /srv/scratch/${env.BUILD_TAG}"
      }
    }
  }

  post {
    failure {
      slackSend color: 'danger', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Failure (<${env.BUILD_URL}|Open>)"
    }

    changed {
      script{
        if('SUCCESS'.equals(currentBuild.currentResult)) {
          slackSend color: 'good', message: "${env.JOB_NAME} - #${env.BUILD_NUMBER} Back to normal (<${env.BUILD_URL}|Open>)"
        }
      }
    }
  }
}
