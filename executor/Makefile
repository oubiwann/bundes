VERSION=0.3.0-snapshot
PREFIX?=/usr/local
GOPATH=$(PWD)/build:$(PWD)
PROGRAM=bundes-executor
GO=env GOPATH=$(GOPATH) go
RM?=rm -f
LN=ln -s
MAIN=bundes_executor.go

all: $(PROGRAM)

$(PROGRAM): $(MAIN) $(SRCS)
				$(GO) build -o $(PROGRAM) $(MAIN)

clean:
				$(RM) $(PROGRAM)
				$(GO) clean

deps:
				$(GO) get github.com/mesos/mesos-go/executor
				$(GO) get github.com/mesos/mesos-go/mesosproto
