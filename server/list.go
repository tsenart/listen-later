package main

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"
)

type List struct {
	sync.RWMutex
	items []Playable
	pos   map[string]int
}

type Playable struct {
	Urn          string    `json:"urn"`
	FinishedAt   time.Time `json:"finished_at"`
	LastPlayedAt time.Time `json:"last_played_at"`
	Progress     uint64    `json:"progress"`
}

func NewList() *List {
	return &List{
		items: make([]Playable, 0),
		pos:   make(map[string]int),
	}
}

func (l *List) Set(urn string, finished, last time.Time, progress uint64) (Playable, error) {
	l.Lock()
	defer l.Unlock()
	pos, ok := l.pos[urn]
	if !ok {
		l.items = append(l.items, Playable{Urn: urn})
		l.pos[urn] = len(l.items) - 1
	}
	if finished.Unix() != 0 {
		l.items[pos].FinishedAt = finished
	}
	if last.Unix() != 0 {
		l.items[pos].LastPlayedAt = last
	}
	if progress != 0 {
		l.items[pos].Progress = progress
	}
	return l.items[pos], nil
}

func (l *List) Delete(urn string) (Playable, error) {
	l.Lock()
	defer l.Unlock()

	pos, ok := l.pos[urn]
	if !ok {
		return Playable{}, fmt.Errorf("`%s` not found", urn)
	}

	playable := l.items[pos]
	if pos == 0 {
		l.items = l.items[1:]
	} else if pos == len(l.items)-1 {
		l.items = l.items[:len(l.items)-1]
	} else {
		updatedItems := make([]Playable, len(l.items)-1)
		copy(updatedItems, l.items[:pos])
		copy(updatedItems[pos:], l.items[pos+1:])
		l.items = updatedItems
	}
	delete(l.pos, urn)

	return playable, nil
}

func (l *List) Size() int {
	l.RLock()
	defer l.RUnlock()
	return len(l.items)
}

func (q *List) MarshalJSON() ([]byte, error) {
	q.RLock()
	defer q.RUnlock()
	return json.Marshal(q.items)
}
