package main

import (
	"log"
	"sync"
)

type Pusher struct {
	sync.RWMutex
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
		log.Printf("Sending %v to %v\n", event, p.DeviceIds())
	}
}

func (p *Pusher) Register(deviceId string) {
	p.Lock()
	defer p.Unlock()
	p.deviceIds[deviceId] = struct{}{}
}

func (p *Pusher) Send(e Event) {
	p.bus <- e
}

func (p *Pusher) DeviceIds() []string {
	p.RLock()
	defer p.RUnlock()

	deviceIds := make([]string, 0, len(p.deviceIds))
	for deviceId, _ := range p.deviceIds {
		deviceIds = append(deviceIds, deviceId)
	}

	return deviceIds
}
