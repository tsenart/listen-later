package main

import (
	"sync"
)

type Pusher struct {
	sync.Mutex
	bus       chan Event
	deviceIds map[string]struct{}
}

type Event struct {
	Name    string `json:"name"`
	Payload string `json:"payload"`
}

func NewPusher() *Pusher {
	pusher := &Pusher{
		bus:       make(chan Event),
		deviceIds: make(map[string]struct{}),
	}

	go pusher.loop()

	return pusher
}

func (p *Pusher) loop() {
	for event := range p.bus {
		// Implement sending to GCM
	}
}

func (p *Pusher) Register(deviceId string) {
	p.Lock()
	defer p.Unlock()

	p.deviceIds[deviceId] = struct{}{}
}

func (p *Pusher) Send(e Event) {
	// Implement sending event to bus
}
