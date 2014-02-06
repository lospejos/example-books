import ratpack.codahale.metrics.CodaHaleMetricsModule
import ratpack.codahale.metrics.HealthCheckEndpoint
import ratpack.example.books.Book
import ratpack.example.books.BookModule
import ratpack.example.books.BookRestEndpoint
import ratpack.example.books.BookService
import ratpack.example.books.DatabaseHealthCheck
import ratpack.groovy.sql.SqlModule
import ratpack.h2.H2Module
import ratpack.jackson.JacksonModule
import ratpack.remote.RemoteControlModule

import ratpack.form.Form
import ratpack.rx.RxModule

import static ratpack.groovy.Groovy.groovyTemplate
import static ratpack.groovy.Groovy.ratpack
import static ratpack.jackson.Jackson.json

ratpack {
    modules {
        register new CodaHaleMetricsModule().jmx()
        register new H2Module()
        register new SqlModule()
        register new JacksonModule()
        register new BookModule()
        register new RemoteControlModule()
        register new RxModule()
        bind DatabaseHealthCheck

        init { BookService bookService ->
            bookService.createTable()
        }
    }

    handlers { BookService bookService ->

        get {
           bookService.all()
           .toList()
           .subscribe { List<Book> books ->
                render groovyTemplate("listing.html", title: "Books", books: books, msg: request.queryParams.msg ?: "")
           }
        }

        handler("create") {
            byMethod {
                get {
                    render groovyTemplate("create.html", title: "Create Book")
                }
                post {
                    def form = parse(Form.class)
                    bookService.insert(form.title, form.content)
                    .single()
                    .subscribe { Long id ->
                        redirect "/?msg=Book+$id+created"
                    }
                }
            }
        }

        handler("update/:id") {
            def id = pathTokens.asLong("id")

            bookService.find(id)
            .single()
            .subscribe { Book book ->
                if (book == null) {
                    clientError(404)
                } else {
                    byMethod {
                        get {
                            render groovyTemplate("update.html", title: "Update Book", book: book)
                        }
                        post {
                            def form = parse(Form.class)
                            bookService.update(id, form.title, form.content)
                            .subscribe {
                                redirect "/?msg=Book+$id+updated"
                            }
                        }
                    }
                }
            }
        }

        post("delete/:id") {
            def id = pathTokens.asLong("id")
            bookService.delete(id)
            .subscribe {
                redirect "/?msg=Book+$id+deleted"
            }
        }

        prefix("api") {
            get("books") {
                bookService.all()
                .toList()
                .subscribe { List<Book> books ->
                    render json(books)
                }
            }
            handler("book/:id?", registry.get(BookRestEndpoint))
        }

        prefix("admin") {
            handler(registry.get(HealthCheckEndpoint))
        }

        assets "public"
    }

}