#include <fcntl.h>
#include <pthread.h>
#include <stddef.h>
#include <string.h>

static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t stopped = PTHREAD_COND_INITIALIZER;
static int quit;
static size_t verified;

int hev_socks5_tunnel_main_from_str(const unsigned char *conf, unsigned int len, int fd) {
    pthread_mutex_lock(&lock);
    while (!quit) pthread_cond_wait(&stopped, &lock);
    if (len == sizeof("socks5: fixture") - 1 && memcmp(conf, "socks5: fixture", len) == 0 &&
        fcntl(fd, F_GETFD) >= 0) verified++;
    quit = 0;
    pthread_mutex_unlock(&lock);
    return 0;
}

void hev_socks5_tunnel_quit(void) {
    pthread_mutex_lock(&lock);
    quit = 1;
    pthread_cond_signal(&stopped);
    pthread_mutex_unlock(&lock);
}

void hev_socks5_tunnel_stats(size_t *a, size_t *b, size_t *c, size_t *d) {
    *a = verified;
    *b = *c = *d = 0;
}
