//
// Created by xu fulong on 2022/9/22.
//

#ifndef LEARNINGMEDIA_PACKETQUEUE_H
#define LEARNINGMEDIA_PACKETQUEUE_H

#include <queue>
#include <thread>

template<typename T>
class PacketQueue {
    typedef void (*ReleaseCallback)(T &);

private:
    std::mutex m_mutex;
    std::queue<T> m_queue;
    bool m_running;

    ReleaseCallback m_callback;

public:

    void setReleaseCallback(ReleaseCallback callback) {
        m_callback = callback;
    }

    void setRunning(bool running) {
        m_running = running;
    }

    bool empty() {
        std::lock_guard<std::mutex> l(m_mutex);
        return m_queue.empty();
    }

    int size() {
        std::lock_guard<std::mutex> l(m_mutex);
        return m_queue.size();
    }

    void push(T value) {
        std::lock_guard<std::mutex> l(m_mutex);
        if (m_running) {
            m_queue.push(value);
        }
    }

    int pop(T &value) {
        int ret = 0;
        std::unique_lock<std::mutex> l(m_mutex);
        if (!m_running) {
            return ret;
        }
        if (!m_queue.empty()) {
            value = m_queue.front();
            m_queue.pop();
            ret = 1;
        }
        return ret;
    }

    void clear() {
        std::lock_guard<std::mutex> l(m_mutex);
        if (m_queue.empty())
            return;
        for (int i = 0; i < m_queue.size(); ++i) {
            T value = m_queue.front();
            m_callback(value);
            m_queue.pop();
        }
    }

};

#endif //LEARNINGMEDIA_PACKETQUEUE_H
