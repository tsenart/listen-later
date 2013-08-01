package main

import (
	ws "code.google.com/p/go.net/websocket"
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"
)

func QueueHandler(queue *Queue) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		out, _ := json.Marshal(queue)
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Content-Length", strconv.Itoa(len(out)))
		w.Write(out)
	}
}

func EnqueueHandler(queue *Queue, bus *EventBus) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		urn := r.URL.Query().Get(":urn")
		if len(urn) == 0 {
			err := errors.New("Could not enqueue empty urn.")
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		queue.Enqueue(urn)
		bus.Notify(Event{"enqueue", urn})
		log.Printf("Added %s to queue %v", urn, queue)

		QueueHandler(queue)(w, r)
	}
}

func DequeueHandler(queue *Queue, bus *EventBus) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if urn, err := queue.Dequeue(); err != nil {
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		} else {
			bus.Notify(Event{"dequeue", urn})
			log.Printf("Removed %s from queue %v", urn, queue)
		}

		QueueHandler(queue)(w, r)
	}
}

func GCMSubscriptionHandler(pusher *GCMPusher) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if deviceId := r.URL.Query().Get("device_id"); len(deviceId) > 0 {
			pusher.Subscribe(deviceId)
		} else {
			err := errors.New("Provided device_id is invalid.")
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
		}
	}
}

func WSSubscriptionHandler(pusher *WSPusher) ws.Handler {
	return ws.Handler(func(conn *ws.Conn) {
		if err := <-pusher.Subscribe(conn); err != nil {
			log.Println(err.Error())
			conn.WriteClose(http.StatusBadRequest)
		}
	})
}
