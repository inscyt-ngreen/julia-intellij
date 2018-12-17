#=
IntelliJ.jl:
- Julia version: 1.0.2
- Author: zxj5470
- Date: 2018-11-23
=#
println("Initialize julia-intellij REPL environment...")

if VERSION >= v"0.7.0"
    using Pkg
    using Sockets
else
    using Compat
end

if "JSON" in keys(Pkg.installed())
    using JSON
else
    Pkg.add("JSON")
    using JSON
end

if "PyCall" in keys(Pkg.installed())
    using PyCall
    @eval @pyimport matplotlib
    function use_intellij_backend()
      pushfirst!(PyVector(pyimport("sys")["path"]),@__DIR__)
      matplotlib.use("module://backend_interagg")
      println("set plot outputs redirect to intellij.")
  end

  println("""execute `use_intellij_backend()` to enable JuliaSciView Plots,
  supported backends if you installed:
      `matplotlib.pyplot`""")

else
   println("""PyCall package not found, to use JuliaSciView Plots,
	 please add PyCall and install matplotlib.pyplot package.""")
end

_intellij_filter_names(v) = string(v) ∉ ["Base", "Core", "Main", "InteractiveUtils", "matplotlib"]

function _intellij_send_to(rows)
    if "JULIA_INTELLIJ_DATA_PORT" in keys(ENV)
        port = parse(Int, ENV["JULIA_INTELLIJ_DATA_PORT"])
        f = connect(getipaddr(), port)
        write(f,json(rows) * "\n")
    end
end

_intellij_stringfy(value) = try JSON.json(value) catch e "<cannot serialize>" end

function _intellij_varinfo(m=Main)
    pattern = "_intellij_"
    rows = Array[ let value = getfield(m, v)
        name = string(v)
        sz = if VERSION >= v"0.7.0"
            @eval using Base:summarysize, format_bytes
            format_bytes(summarysize(value))
            else
                ""
            end
        size = value === Main || value === Base || value === Core ? "" : sz
        type_info = summary(value)
        info = type_info
        if size == "0 bytes" && occursin("typeof(",type_info)
            type_info = "function"
        else
            info = _intellij_stringfy(value)
        end

        String[name, size, info, type_info]
        end
        for v in sort!(names(m)) if isdefined(m, v) &&
            !occursin(pattern, string(v)) && _intellij_filter_names(v)]

    _intellij_send_to(rows)
    return
end

println("\nInitialize done.\n")