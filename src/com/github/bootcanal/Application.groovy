package com.github.bootcanal

import com.github.bootcanal.Docker

class Application {

    def docker

    def prune(script) {
        if (this.docker == null) {
            this.docker = new Docker()
        }
        this.docker.prune(script)
    }
}