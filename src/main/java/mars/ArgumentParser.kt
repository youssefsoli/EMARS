package mars

import net.sourceforge.argparse4j.impl.Arguments
import net.sourceforge.argparse4j.inf.Argument
import net.sourceforge.argparse4j.inf.ArgumentParser
import net.sourceforge.argparse4j.inf.Namespace
import kotlin.collections.HashMap
import kotlin.reflect.KProperty


val ARG_PARSER_MAP = HashMap<ArgumentParser, Namespace>()

fun ArgumentParser.parseToVars(args: Array<String>) = parseArgs(args).also { ARG_PARSER_MAP[this] = it }

class ArgParserDelegate<T>(val a: ArgumentParser, val name: String)
{
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T?
    {
        return ARG_PARSER_MAP[a]?.get(name)
    }
}

fun <T> ArgumentParser.arg(vararg names: String, help: String? = null, apply: Argument.() -> Unit = {}): ArgParserDelegate<T>
{
    val varname = names.last().trimStart('-').replace('-', '_')
    val arg = addArgument(*names).dest(varname)
    if (help != null) arg.help(help)
    apply(arg)
    return ArgParserDelegate(this, varname)
}

fun ArgumentParser.string(vararg names: String, help: String? = null, apply: Argument.() -> Unit = {}) =
    arg<String>(*names, help=help, apply=apply)

fun ArgumentParser.flag(vararg names: String, help: String? = null, apply: Argument.() -> Unit = {}) =
    arg<Boolean>(*names, help=help) { action(Arguments.storeTrue()); apply() }

