package main

import (
	"fmt"
	"strings"
	"sync"
)

// Queue provides a naive implementation of a queue.
// The underlying data structure is a slice which will
// grow unbounded and not reuse any freed up space.
type Queue struct {
	sync.RWMutex
	items []string
}

func NewQueue() *Queue {
	return &Queue{items: make([]string, 0)}
}

func (q *Queue) Enqueue(urn string) {
	q.Lock()
	q.items = append(q.items, urn)
	q.Unlock()
}

func (q *Queue) Dequeue() (string, error) {
	q.Lock()
	defer q.Unlock()

	if len(q.items) == 0 {
		return "", fmt.Errorf("Queue is empty")
	}

	head := q.items[0]
	q.items = q.items[1:]

	return head, nil
}

func (q *Queue) Size() int {
	q.RLock()
	defer q.RUnlock()
	return len(q.items)
}

func (q *Queue) String() string {
	q.RLock()
	defer q.RUnlock()
	return fmt.Sprintf("%v", q.items)
}

func (q *Queue) MarshalJSON() ([]byte, error) {
	q.RLock()
	defer q.RUnlock()
	return []byte(strings.Join(q.items, ", ")), nil
}
