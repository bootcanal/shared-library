#!groovy

import com.github.bootcanal.GitHub

def call(Map config) {
    def github = new GitHub()

    pipeline {
        agent none

        environment {
            XDG_CACHE_HOME = "/tmp/.cache"
            GOOS = "linux"
            GOARCH = "amd64"
            CGO_ENABLED = "0"
            GITHUB_CREDS = credentials('CANAL_TOKEN')
            UNIT_TEST_TARGET = 'unit-test'
            CHANGE_URL = ''
            CHANGE_ID = ''
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
                            script {
                                def git_user = "$GITHUB_CREDS_USR"
                                print git_user.collect{it}
                                print "this is git user"
                                def git_token = "$GITHUB_CREDS_PSW"
                                print git_token.collect{it}
                                print "this is git token"
                            }

                            withCredentials([usernamePassword(credentialsId: 'CANAL_ACCOUNT', passwordVariable: 'token', usernameVariable: 'username')]) {
                                github.test()
                                github.setInfo(this, 'test')
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
                        post {
                            success {
                                script {
                                    github.init(this)
                                }
                            }
                            failure {
                                script {
                                    github.setState(this)
                                }
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
                            script {
                                env.COMMIT_ID = sh(script: "git rev-parse HEAD", returnStdout: true).trim()
                            }
                            echo 'Building Go App'
                            echo 'go build -o ' + config.name + ' ' + config.main
                            sh 'go build -o ' + config.name + ' ' + config.main
                            stash includes: 'bin/**', name: 'bin'
                        }
                        post {
                            failure {
                                script {
                                    github.setState(this)
                                }
                            }
                        }
                    }
                    stage('Check Pull Request') {
                        steps {
                            echo 'Check pull request merged'
                            script {
                                github.checkPullRequest(this)
                            }
                        }
                    }
                    stage('Test') {
                        parallel {
                            stage('Run Go Unit Tests') {
                                steps {
                                    echo 'Running Go Unit Tests'
                                    //sh 'go test ./...'
                                    //sh 'go test -v -coverpkg=./... -coverprofile=profile.cov ./...'
                                    sh 'go test -coverpkg=./... -coverprofile=unit-test.cov ./...'
                                }
                                post {
                                    always {
                                        echo "print unit test result: ${currentBuild.result}, ${currentBuild.currentResult}"
                                    }
                                    failure {
                                        echo "unit test result: ${currentBuild.result}, ${currentBuild.currentResult}"
                                        echo "git commit: ${COMMIT_ID}"

                                        script {
                                            github.statusHandle(this, env.UNIT_TEST_TARGET)
                                            github.reviewHandle(this, 'COMMENT')
                                        }
                                    }
                                    success {
                                        script {
                                            github.statusHandle(this, env.UNIT_TEST_TARGET)
                                            coverage = this.sh(returnStdout: true, script: "go tool cover -func unit-test.cov | grep total | awk '{printf \"%.2f\", substr(\$3, 1, length(\$3)-1) / 100}'")
                                        }
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
            }

            stage('Deploy') {
                stages {
                    stage('Docker Build') {
                        steps {
                            script {
                                if (github.getMergedStatus(this)) {
                                    echo 'Building Docker Image'
                                }
                            }
                        }
                    }
                    stage('Docker Push') {
                        steps {
                            script {
                                if (github.getMergedStatus(this)) {
                                    echo 'Pushing Docker Image to ECR'
                                }
                            }
                        }
                    }
                }
                post {
                    success {
                        script {
                            github.tagHandle(this)
                        }
                    }
                }
            }
        }
    }
}