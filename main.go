package main

import (
	"flag"
	"github.com/gorilla/pat"
	"log"
	"net/http"
)

var (
	listen = flag.String("listen", "localhost:9110", "HTTP service listen address")
	gcmKey = flag.String("gcmkey", "", "GCM API Key")
)

func init() {
	flag.Parse()
}

func main() {
	queue := NewQueue()
	pusher, err := NewPusher(*gcmKey)
	if err != nil {
		log.Fatal(err)
	}

	r := pat.New()
	r.Get("/queue", QueueHandler(queue, pusher))
	r.Post("/enqueue/{urn}", EnqueueHandler(queue, pusher))
	r.Post("/dequeue", DequeueHandler(queue, pusher))

	err = http.ListenAndServe(*listen, r)
	if err != nil {
		log.Fatal(err)
	}
}
