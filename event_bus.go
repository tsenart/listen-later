package main

type EventBus struct {
	subscribers []Subscriber
	events      chan Event
}

type Subscriber interface {
	Notify(e Event)
}

type Event struct {
	Name    string `json:"name"`
	Payload string `json:"payload"`
}

func NewEventBus(subscribers []Subscriber) *EventBus {
	bus := &EventBus{
		subscribers: subscribers,
		events:      make(chan Event),
	}
	go bus.loop()
	return bus
}

func (bus *EventBus) Notify(e Event) {
	bus.events <- e
}

func (bus *EventBus) loop() {
	for event := range bus.events {
		for _, subscriber := range bus.subscribers {
			subscriber.Notify(event)
		}
	}
}
