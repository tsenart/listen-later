package main

import (
	ws "code.google.com/p/go.net/websocket"
	"fmt"
	gcm "github.com/googollee/go-gcm"
	"log"
	"net"
	"sync"
)

type GCMPusher struct {
	sync.RWMutex
	events    chan Event
	deviceIds map[string]struct{}
	client    *gcm.Client
}

func NewGCMPusher(key string) *GCMPusher {
	pusher := &GCMPusher{
		events:    make(chan Event),
		deviceIds: make(map[string]struct{}),
	}
	if len(key) != 0 {
		pusher.client = gcm.New(key)
	}
	go pusher.loop()

	return pusher
}

func (p *GCMPusher) Notify(event Event) {
	p.events <- event
}

func (p *GCMPusher) Subscribe(deviceId string) {
	p.Lock()
	p.deviceIds[deviceId] = struct{}{}
	log.Printf("Subscribed `%s` to GSMPush", deviceId)
	p.Unlock()
}

func (p *GCMPusher) loop() {
	for event := range p.events {
		deviceIds := p.DeviceIds()
		if len(deviceIds) == 0 || p.client == nil {
			log.Printf("Bypassing GSM push: %v", event)
			continue
		}
		message := gcm.NewMessage(deviceIds...)
		message.SetPayload(event.Name, event.Payload)
		_, err := p.client.Send(message)
		if err != nil {
			log.Printf("Failed GSM push: %s", err)
			continue
		}
		log.Printf("GSMPushed: %v to %v", event, deviceIds)
	}
}

func (p *GCMPusher) DeviceIds() []string {
	p.RLock()
	defer p.RUnlock()
	deviceIds := make([]string, 0, len(p.deviceIds))
	for deviceId, _ := range p.deviceIds {
		deviceIds = append(deviceIds, deviceId)
	}
	return deviceIds
}

type WSPusher struct {
	sync.RWMutex
	events chan Event
	conns  map[net.Addr]*ws.Conn
}

func NewWSPusher() *WSPusher {
	pusher := &WSPusher{
		events: make(chan Event),
		conns:  make(map[net.Addr]*ws.Conn),
	}
	go pusher.loop()
	return pusher
}

func (p *WSPusher) Notify(event Event) {
	p.events <- event
}

func (p *WSPusher) Subscribe(conn *ws.Conn) chan (error) {
	exit := make(chan error)

	p.Lock()
	defer p.Unlock()

	addr := conn.RemoteAddr()
	if _, ok := p.conns[addr]; ok {
		go func() { exit <- fmt.Errorf("%s already connected", addr) }()
	} else {
		p.conns[addr] = conn
		log.Printf("Subscribed `%s` to WSPusher.", addr)
	}
	// TODO: Find a way to signal correct termination
	return exit
}

func (p *WSPusher) loop() {
	for event := range p.events {
		for addr, conn := range p.conns {
			go func(event Event, addr net.Addr, conn *ws.Conn) {
				if err := ws.JSON.Send(conn, event); err != nil {
					log.Printf("Failed WS push to %s: %s", addr, err)
					return
				}
				log.Printf("WSPushed `%v` event to %s.", event, addr)
			}(event, addr, conn)
		}
	}
}
