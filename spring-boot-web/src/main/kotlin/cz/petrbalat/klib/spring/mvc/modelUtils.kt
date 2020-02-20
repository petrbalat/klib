package cz.petrbalat.klib.spring.mvc

import org.springframework.web.servlet.ModelAndView


operator fun ModelAndView.get(model:String) : Any? = this.model[model]
operator fun ModelAndView.set(model: String, value:Any?) {
    this.model[model] = value
}

inline fun <reified T> ModelAndView.getModel(model: String): T? = (this.model[model] as? T)
