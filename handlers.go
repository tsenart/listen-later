package main

import (
	"encoding/json"
	"errors"
	"log"
	"net/http"
	"strconv"
)

func QueueHandler(queue *Queue, pusher *Pusher) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if deviceId := r.URL.Query().Get("device_id"); len(deviceId) > 0 {
			pusher.Register(deviceId)
		}
		out, _ := json.Marshal(queue)
		log.Printf("OUT %s\n", out)
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Content-Length", strconv.Itoa(len(out)))
		w.Write(out)
	}
}

func EnqueueHandler(queue *Queue, pusher *Pusher) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		urn := r.URL.Query().Get(":urn")
		if len(urn) == 0 {
			err := errors.New("Could not enqueue empty urn.")
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		queue.Enqueue(urn)
		pusher.Send(Event{"enqueue", urn})
		log.Printf("Added %s to queue %v\n", urn, queue)

		QueueHandler(queue, pusher)(w, r)
	}
}

func DequeueHandler(queue *Queue, pusher *Pusher) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if queue.Size() == 0 {
			err := errors.New("Queue is empty.")
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}

		if urn, err := queue.Dequeue(); err != nil {
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		} else {
			pusher.Send(Event{"dequeue", urn})
			log.Printf("Removed %s from queue %v\n", urn, queue)
		}

		QueueHandler(queue, pusher)(w, r)
	}
}
