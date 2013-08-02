package main

import (
	ws "code.google.com/p/go.net/websocket"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/http"
	"strconv"
	"time"
)

func ShowHandler(obj interface{}) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		out, err := json.Marshal(obj)
		if err != nil {
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusInternalServerError)
			return
		}
		w.Header().Set("Content-Type", "application/json")
		w.Header().Set("Content-Length", strconv.Itoa(len(out)))
		w.Write(out)
	}
}

func SetHandler(list *List, bus *EventBus) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		var urn string
		var finished, last time.Time
		var progress uint64
		var err error

		urn = r.URL.Query().Get(":urn")
		if len(urn) == 0 {
			err := fmt.Errorf("Empty urn")
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		if r.URL.Query().Get("finished_at") != "" {
			finished, err = time.Parse(time.RFC3339, r.URL.Query().Get("finished_at"))
			if err != nil {
				log.Println(err.Error())
				http.Error(w, err.Error(), http.StatusBadRequest)
				return
			}
		}
		if r.URL.Query().Get("last_played_at") != "" {
			last, err = time.Parse(time.RFC3339, r.URL.Query().Get("last_played_at"))
			if err != nil {
				log.Println(err.Error())
				http.Error(w, err.Error(), http.StatusBadRequest)
				return
			}
		}
		if r.URL.Query().Get("progress") != "" {
			progress, err = strconv.ParseUint(r.URL.Query().Get("progress"), 10, 64)
			if err != nil {
				log.Println(err.Error())
				http.Error(w, err.Error(), http.StatusBadRequest)
				return
			}
		}

		playable := list.Set(urn, finished, last, progress)
		bus.Notify(Event{"set", *playable})
		log.Printf("Updated `%s` on list", urn)

		ShowHandler(playable)(w, r)
	}
}

func PlaybackHandler(list *List, bus *EventBus) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		action := r.URL.Query().Get(":action")
		urn := r.URL.Query().Get(":urn")

		playable, err := list.Find(urn)
		if err != nil {
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		bus.Notify(Event{action, *playable})

		ShowHandler(action)(w, r)
	}
}

func DeleteHandler(list *List, bus *EventBus) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		urn := r.URL.Query().Get(":urn")
		if len(urn) == 0 {
			err := fmt.Errorf("Empty urn")
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		playable, err := list.Delete(urn)
		if err != nil {
			log.Println(err.Error())
			http.Error(w, err.Error(), http.StatusBadRequest)
			return
		}
		bus.Notify(Event{"delete", *playable})
		log.Printf("Deleted `%s` from list", urn)

		ShowHandler(playable)(w, r)
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
