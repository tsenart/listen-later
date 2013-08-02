package main

import (
	"container/list"
	"encoding/json"
	"fmt"
	"sync"
	"time"
)

type List struct {
	sync.RWMutex
	list.List
}

type Playable struct {
	Urn          string    `json:"urn"`
	FinishedAt   time.Time `json:"finished_at"`
	LastPlayedAt time.Time `json:"last_played_at"`
	Progress     uint64    `json:"progress"`
	ToggleAt     time.Time `json:"toggle_at,omitempty"`
}

func NewList() *List {
	return &List{List: list.List{}}
}

func (l *List) Set(urn string, finished, last time.Time, progress uint64) *Playable {
	playable := &Playable{Urn: urn}

	if finished.Unix() != 0 {
		playable.FinishedAt = finished
	}
	if last.Unix() != 0 {
		playable.LastPlayedAt = last
	}
	if progress != 0 {
		playable.Progress = progress
	}

	l.Lock()
	defer l.Unlock()

	for e := l.Front(); e != nil; e = e.Next() {
		p := e.Value.(*Playable)
		if p.Urn == urn {
			p.FinishedAt = playable.FinishedAt
			p.LastPlayedAt = playable.LastPlayedAt
			p.Progress = playable.Progress
			return p
		}
	}
	l.PushBack(playable)
	return playable
}

func (l *List) Delete(urn string) (*Playable, error) {
	l.Lock()
	defer l.Unlock()
	for e := l.Front(); e != nil; e = e.Next() {
		p := e.Value.(*Playable)
		if urn == p.Urn {
			l.Remove(e)
			return p, nil
		}
	}
	return &Playable{}, fmt.Errorf("`%s` not found", urn)
}

func (l *List) Find(urn string) (*Playable, error) {
	l.RLock()
	defer l.RUnlock()
	for e := l.Front(); e != nil; e = e.Next() {
		p := e.Value.(*Playable)
		if urn == p.Urn {
			return p, nil
		}
	}
	return &Playable{}, fmt.Errorf("`%s` not found", urn)
}

func (l *List) MarshalJSON() ([]byte, error) {
	items := make([]*Playable, l.Len())
	var count int

	l.RLock()
	for e := l.Front(); e != nil; e = e.Next() {
		items[count] = e.Value.(*Playable)
		count++
	}
	l.RUnlock()

	return json.Marshal(items)
}
