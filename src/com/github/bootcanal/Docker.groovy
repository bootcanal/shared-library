package com.github.bootcanal

class Docker {
    /**
     * prune docker image
     */
    def prune(script) {
        script.sh 'docker image prune --all --force'
        script.sh 'printenv'
        script.echo 'echo GIT_URL: ' + script.env.GIT_URL
    }
}