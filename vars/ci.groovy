#!groovy

import com.github.bootcanal.*

def call(Map config) {
    def commit_id = "$(git rev-parse HEAD)"
    pipeline {
        agent none

        environment {
            XDG_CACHE_HOME = "/tmp/.cache"
            GOOS = "linux"
            GOARCH = "amd64"
            CGO_ENABLED = "0"
            GITHUB_CREDS = credentials('DEVCX-GAMBIT-GITHUB')
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
//                            echo "github user: $GITHUB_CREDS_USR"
                            script {
                                def git_user = "$GITHUB_CREDS_USR"
                                print git_user.collect{it}
                                print "this is git user"
                                def git_token = "$GITHUB_CREDS_PSW"
                                print git_token.collect{it}
                                print "this is git token"
                            }
//                            echo "$GITHUB_CREDS_USR".collect {it }
//                            echo "$GITHUB_CREDS_PSW".collect { it}
//                            echo "github token: $GITHUB_CREDS_PSW"

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
                                    //sh 'go test ./...'
                                    sh 'go test -v -coverpkg=./... -coverprofile=profile.cov ./... && go tool cover -func profile.cov'
                                }
                                post {
                                    always {
                                        echo "print unit test result: ${currentBuild.result}, ${currentBuild.currentResult}"
                                    }
                                    failure {
                                        echo "unit test result: ${currentBuild.result}, ${currentBuild.currentResult}"
                                        GitHub.checkPR($GITHUB_CREDS_PSW, 'bootcanal', 'canal', commit_id, 'failure')
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
                        echo "current success result: ${currentBuild.result}"
                    }
                    failure {
                        echo "current failure result: ${currentBuild.result}"
                    }
                }
            }
        }
    }
}