import com.flora.codegen.TemplateFunction;

module com.flora.ramet {
    requires com.flora.root;
    requires org.jetbrains.annotations;
    uses TemplateFunction;
    exports com.flora.codegen;
}
