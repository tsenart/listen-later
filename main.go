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
	gcmPusher := NewGCMPusher(*gcmKey)
	wsPusher := NewWSPusher()
	bus := NewEventBus([]Subscriber{gcmPusher, wsPusher})

	r := pat.New()
	r.Get("/queue", QueueHandler(queue))
	r.Post("/enqueue/{urn}", EnqueueHandler(queue, bus))
	r.Post("/dequeue", DequeueHandler(queue, bus))
	r.Handle("/subscribe/gcm", GCMSubscriptionHandler(gcmPusher))
	r.Handle("/subscribe/ws", WSSubscriptionHandler(wsPusher))

	err := http.ListenAndServe(*listen, r)
	if err != nil {
		log.Fatal(err)
	}
}
