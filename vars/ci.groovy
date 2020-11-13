#!groovy.

def call(Map config) {
    pipeline {
        agent none

        environment {
            XDG_CACHE_HOME = "/tmp/.cache"
            GOOS = "linux"
            GOARCH = "amd64"
            CGO_ENABLED = "0"
        }

        stages {
            stage('Init') {
                agent {
                    docker {
                        image 'golang:1.13'
                        label 'docker'
                        args '-u root:sudo -v "${PWD}":/go -w /go'
                    }
                }
                stages {
                    stage('Initializing Git') {
                        echo 'Setting up GitHub Access'
                    }
                    stage('Initializing Go') {
                        when {
                            expression {
                                fileExists '/go/go.mod'
                            }
                        }
                        steps {
                            echo 'Building Go App'
                            sh 'go build -o ' + config.name + ' ' + config.main
                            stash includes: 'bin/**', name: 'bin'
                        }
                    }
                }
                post {
                    always {
                        cleanWs deleteDirs: true
                    }
                }
            }
        }
    }
}