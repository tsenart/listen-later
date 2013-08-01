package main

import (
	gcm "github.com/googollee/go-gcm"
	"log"
	"sync"
)

type Pusher struct {
	sync.RWMutex
	bus       chan Event
	deviceIds map[string]struct{}
	client    *gcm.Client
}

type Event struct {
	Name    string `json:"name"`
	Payload string `json:"payload"`
}

func NewPusher(key string) *Pusher {
	pusher := &Pusher{
		bus:       make(chan Event),
		deviceIds: make(map[string]struct{}),
	}

	if len(key) != 0 {
		pusher.client = gcm.New(key)
	}

	go pusher.loop()

	return pusher
}

func (p *Pusher) loop() {
	for event := range p.bus {
		deviceIds := p.DeviceIds()
		if len(deviceIds) == 0 {
			continue
		}

		log.Printf("Sending %v to %v\n", event, deviceIds)
		if p.client == nil {
			continue
		}
		message := gcm.NewMessage(deviceIds...)
		message.SetPayload(event.Name, event.Payload)
		_, err := p.client.Send(message)
		if err != nil {
			log.Printf("Error: Failed to send `%v`: %s", event, err)
		}
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
