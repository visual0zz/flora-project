package com.flora.ramet.idea.parser;

import com.flora.ramet.idea.RametLanguage;
import com.flora.ramet.idea.lexer.RametLexer;
import com.flora.ramet.idea.lexer.RametTokenTypes;
import com.flora.ramet.idea.parser.RametTypes;
import com.flora.ramet.idea.psi.RametDirectivePsi;
import com.flora.ramet.idea.psi.RametMacroCallPsi;
import com.flora.ramet.idea.psi.RametVarInterpolationPsi;
import com.flora.ramet.idea.psi.RametFile;
import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

/**
 * Ramet 模板语言的 ParserDefinition。
 */
public class RametParserDefinition implements ParserDefinition {

    private static final Logger LOG = Logger.getInstance(RametParserDefinition.class);
    private static final IFileElementType FILE_TYPE =
            new IFileElementType(RametLanguage.INSTANCE);

    @Override
    public @NotNull Lexer createLexer(Project project) {
        LOG.info("createLexer() called");
        return new RametLexer();
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        LOG.info("createParser() called");
        return new RametParser();
    }

    @Override
    public @NotNull IFileElementType getFileNodeType() {
        LOG.info("getFileNodeType() called");
        return FILE_TYPE;
    }

    @Override
    public @NotNull TokenSet getCommentTokens() {
        LOG.info("getCommentTokens() called");
        return TokenSet.create(RametTokenTypes.COMMENT);
    }

    @Override
    public @NotNull TokenSet getWhitespaceTokens() {
        return TokenSet.create(RametTokenTypes.WHITESPACE);
    }

    @Override
    public @NotNull TokenSet getStringLiteralElements() {
        LOG.info("getStringLiteralElements() called");
        return TokenSet.create(RametTokenTypes.STRING);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        LOG.info("createFile() called");
        return new RametFile(viewProvider);
    }

    @Override
    public @NotNull SpaceRequirements spaceExistenceTypeBetweenTokens(ASTNode left, ASTNode right) {
        return SpaceRequirements.MAY;
    }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        IElementType type = node.getElementType();
        if (type == RametTypes.VAR_INTERPOLATION) return new RametVarInterpolationPsi(node);
        if (type == RametTypes.DIRECTIVE) return new RametDirectivePsi(node);
        if (type == RametTypes.MACRO_CALL) return new RametMacroCallPsi(node);
        return new ASTWrapperPsiElement(node);
    }
}
