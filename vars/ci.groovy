#!groovy

import com.github.bootcanal.*

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
                        args '-u root:sudo -v "${PWD}":/go -w /go'
                    }
                }
                stages {
                    stage('Initializing Git') {
                        steps {
                            echo 'Setting up GitHub Access'
                            withCredentials([usernamePassword(credentialsId: 'DEVCX-GAMBIT-GITHUB', passwordVariable: 'token', usernameVariable: 'username')]) {
                                echo 'token:'
                                sh 'echo "${token}"'
                                print token
                                print 'token.collect { it } = ' + token.collect { it }
                                echo 'username:'
                                sh 'echo "${username}"'
                                print username
                                print 'username.collect { it } =' + username.collect {it }
                            }
                        }
                    }
                    stage('Initializing Go') {
                        when {
                            expression {
                                fileExists 'go.mod'
                            }
                        }
                        steps {
                            echo 'Building Go App'
                            echo 'go build -o ' + config.name + ' ' + config.main
                            sh 'go build -o ' + config.name + ' ' + config.main
                            stash includes: 'bin/**', name: 'bin'
                        }
                    }
                    stage('Test') {
                        parallel {
                            stage('Run Go Unit Tests') {
                                steps {
                                    echo 'Running Go Unit Tests'
                                    sh 'go test ./...'
                                }
                                post {
                                    failure {
                                        echo 'unit test result: ' + "${currentBuild.result}"
                                    }
                                }
                            }
                            stage('Run API Tests') {
                                steps {
                                    echo 'Running API Tests'
                                }
                            }
                        }
                    }
                }
                post {
                    success {
                        echo 'current success result: ' + $currentBuild.result
                    }
                    failure {
                        echo 'current failure result: ' + $currentBuild.result
                    }
                }
            }
        }
    }
}